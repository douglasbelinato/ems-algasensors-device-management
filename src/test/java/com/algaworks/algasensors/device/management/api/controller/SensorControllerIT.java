package com.algaworks.algasensors.device.management.api.controller;

import com.algaworks.algasensors.device.management.api.client.SensorMonitoringClient;
import com.algaworks.algasensors.device.management.api.model.SensorInput;
import com.algaworks.algasensors.device.management.common.IdGenerator;
import com.algaworks.algasensors.device.management.domain.model.Sensor;
import com.algaworks.algasensors.device.management.domain.model.SensorId;
import com.algaworks.algasensors.device.management.domain.repository.SensorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SensorControllerIT {

    private static final String SENSORS_PATH = "/api/sensors";
    private static final String SENSOR_PATH = "/api/sensors/{id}";
    private static final String SENSOR_ENABLE_PATH = "/api/sensors/{id}/enable";

    @LocalServerPort
    private int port;

    @Autowired
    private SensorRepository sensorRepository;

    @MockitoBean
    private SensorMonitoringClient sensorMonitoringClient;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        sensorRepository.deleteAll();
    }

    @Nested
    @DisplayName("POST /api/sensors")
    class Create {

        @Test
        @DisplayName("should create a sensor and return 201 with the persisted body")
        void shouldCreateSensor() {
            SensorInput input = newInput("Temperature Sensor", "10.0.0.1", "Server Room", "MQTT", "TS-100");

            client.post().uri(SENSORS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(input)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.id").exists()
                    .jsonPath("$.name").isEqualTo("Temperature Sensor")
                    .jsonPath("$.ip").isEqualTo("10.0.0.1")
                    .jsonPath("$.location").isEqualTo("Server Room")
                    .jsonPath("$.protocol").isEqualTo("MQTT")
                    .jsonPath("$.model").isEqualTo("TS-100")
                    .jsonPath("$.enabled").isEqualTo(false);

            assertEquals(1, sensorRepository.count());
        }

        @Test
        @DisplayName("should return 400 when the request body is missing")
        void shouldReturnBadRequestWhenBodyIsMissing() {
            client.post().uri(SENSORS_PATH)
                    .exchange()
                    .expectStatus().isBadRequest();

            assertEquals(0, sensorRepository.count());
        }
    }

    @Nested
    @DisplayName("GET /api/sensors/{id}")
    class GetOne {

        @Test
        @DisplayName("should return 200 with the sensor when it exists")
        void shouldReturnSensor() {
            Sensor sensor = persistSensor("Pressure Sensor", "10.0.0.2", "Lab", "HTTP", "PS-200", true);

            client.get().uri(SENSOR_PATH, idOf(sensor))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(idOf(sensor))
                    .jsonPath("$.name").isEqualTo("Pressure Sensor")
                    .jsonPath("$.ip").isEqualTo("10.0.0.2")
                    .jsonPath("$.location").isEqualTo("Lab")
                    .jsonPath("$.protocol").isEqualTo("HTTP")
                    .jsonPath("$.model").isEqualTo("PS-200")
                    .jsonPath("$.enabled").isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("GET /api/sensors")
    class Search {

        @Test
        @DisplayName("should return an empty page when there are no sensors")
        void shouldReturnEmptyPage() {
            client.get().uri(SENSORS_PATH)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").isEmpty()
                    .jsonPath("$.totalElements").isEqualTo(0)
                    .jsonPath("$.totalPages").isEqualTo(0);
        }

        @Test
        @DisplayName("should return all sensors in the page content")
        void shouldReturnAllSensors() {
            persistSensor("Sensor A", "10.0.0.1", "Room A", "MQTT", "A-1", false);
            persistSensor("Sensor B", "10.0.0.2", "Room B", "HTTP", "B-1", true);

            client.get().uri(SENSORS_PATH)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(2)
                    .jsonPath("$.totalElements").isEqualTo(2)
                    .jsonPath("$.totalPages").isEqualTo(1);
        }

        @ParameterizedTest(name = "size={0} -> content={1}, totalPages={2}")
        @DisplayName("should paginate the results according to the page size")
        @CsvSource({
                "2, 2, 3",
                "3, 3, 2",
                "5, 5, 1"
        })
        void shouldPaginateResults(int size, int expectedContentLength, int expectedTotalPages) {
            for (int i = 1; i <= 5; i++) {
                persistSensor("Sensor " + i, "10.0.0." + i, "Room " + i, "MQTT", "M-" + i, false);
            }

            client.get().uri(SENSORS_PATH + "?page=0&size={size}", size)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(expectedContentLength)
                    .jsonPath("$.totalElements").isEqualTo(5)
                    .jsonPath("$.totalPages").isEqualTo(expectedTotalPages)
                    .jsonPath("$.size").isEqualTo(size)
                    .jsonPath("$.number").isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("PUT /api/sensors/{id}")
    class Update {

        @Test
        @DisplayName("should update the sensor and return 200 with the new values")
        void shouldUpdateSensor() {
            Sensor sensor = persistSensor("Old Name", "10.0.0.1", "Old Location", "HTTP", "OLD-1", true);
            SensorInput input = newInput("New Name", "10.0.0.99", "New Location", "MQTT", "NEW-1");

            client.put().uri(SENSOR_PATH, idOf(sensor))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(input)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(idOf(sensor))
                    .jsonPath("$.name").isEqualTo("New Name")
                    .jsonPath("$.ip").isEqualTo("10.0.0.99")
                    .jsonPath("$.location").isEqualTo("New Location")
                    .jsonPath("$.protocol").isEqualTo("MQTT")
                    .jsonPath("$.model").isEqualTo("NEW-1")
                    .jsonPath("$.enabled").isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("DELETE /api/sensors/{id}")
    class Delete {

        @Test
        @DisplayName("should delete the sensor, return 204 and make it unreachable")
        void shouldDeleteSensor() {
            Sensor sensor = persistSensor("To Delete", "10.0.0.1", "Room", "HTTP", "D-1", false);

            client.delete().uri(SENSOR_PATH, idOf(sensor))
                    .exchange()
                    .expectStatus().isNoContent()
                    .expectBody().isEmpty();

            assertEquals(0, sensorRepository.count());

            client.get().uri(SENSOR_PATH, idOf(sensor))
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("PUT/DELETE /api/sensors/{id}/enable")
    class EnableDisable {

        @ParameterizedTest(name = "{0} /enable: {1} -> {2}")
        @DisplayName("should toggle the enabled state and return 204")
        @CsvSource({
                "PUT, false, true",
                "DELETE, true, false"
        })
        void shouldToggleEnabledState(String method, boolean initialState, boolean expectedState) {
            Sensor sensor = persistSensor("Toggle", "10.0.0.1", "Room", "HTTP", "T-1", initialState);

            client.method(HttpMethod.valueOf(method)).uri(SENSOR_ENABLE_PATH, idOf(sensor))
                    .exchange()
                    .expectStatus().isNoContent()
                    .expectBody().isEmpty();

            client.get().uri(SENSOR_PATH, idOf(sensor))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.enabled").isEqualTo(expectedState);
        }
    }

    @Nested
    @DisplayName("Not found scenarios")
    class NotFound {

        @ParameterizedTest(name = "{0} {1} -> 404")
        @DisplayName("should return 404 when the sensor does not exist")
        @CsvSource({
                "GET,    /api/sensors/{id},        false",
                "PUT,    /api/sensors/{id},        true",
                "DELETE, /api/sensors/{id},        false",
                "PUT,    /api/sensors/{id}/enable, false",
                "DELETE, /api/sensors/{id}/enable, false"
        })
        void shouldReturnNotFound(String method, String pathTemplate, boolean needsBody) {
            String missingId = IdGenerator.generateTSID().toString();

            RestTestClient.RequestBodySpec request =
                    client.method(HttpMethod.valueOf(method)).uri(pathTemplate, missingId);

            RestTestClient.ResponseSpec response = needsBody
                    ? request.contentType(MediaType.APPLICATION_JSON)
                        .body(newInput("Any", "10.0.0.1", "Any", "HTTP", "ANY")).exchange()
                    : request.exchange();

            response.expectStatus().isNotFound();
        }
    }

    private SensorInput newInput(String name, String ip, String location, String protocol, String model) {
        SensorInput input = new SensorInput();
        input.setName(name);
        input.setIp(ip);
        input.setLocation(location);
        input.setProtocol(protocol);
        input.setModel(model);
        return input;
    }

    private Sensor persistSensor(String name, String ip, String location, String protocol,
                                 String model, boolean enabled) {
        return sensorRepository.saveAndFlush(Sensor.builder()
                .id(new SensorId(IdGenerator.generateTSID()))
                .name(name)
                .ip(ip)
                .location(location)
                .protocol(protocol)
                .model(model)
                .enabled(enabled)
                .build());
    }

    private String idOf(Sensor sensor) {
        return sensor.getId().getValue().toString();
    }
}