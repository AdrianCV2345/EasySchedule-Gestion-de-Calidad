package com.easyschedule.backend;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private static final String STATUS = "status";
    private static final String DATABASE = "database";
    private static final String ERROR = "error";
    private static final String UP = "UP";
    private static final String DOWN = "DOWN";

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) {
                return ResponseEntity.ok(Map.of(STATUS, UP, DATABASE, UP));
            }
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(STATUS, DOWN, DATABASE, "INVALID_CONNECTION"));
        } catch (SQLException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(STATUS, DOWN, DATABASE, DOWN, ERROR, e.getMessage()));
        }
    }
}
