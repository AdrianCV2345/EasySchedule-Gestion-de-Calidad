package com.easyschedule.backend.academico.horario.controller;

import com.easyschedule.backend.academico.horario.dto.HorarioActualResponse;
import com.easyschedule.backend.academico.horario.service.HorarioRecomendadoService;
import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/academico/horario")
public class HorarioRecomendadoController {

	private static final String FORMATO_IMAGEN = "imagen";

	private final HorarioRecomendadoService horarioRecomendadoService;

	public HorarioRecomendadoController(HorarioRecomendadoService horarioRecomendadoService) {
		this.horarioRecomendadoService = horarioRecomendadoService;
	}

	@GetMapping("/actual")
	public HorarioActualResponse getHorarioActual(Principal principal) {
		return horarioRecomendadoService.getHorarioActualByUserId(getAuthenticatedUserId(principal));
	}

	@GetMapping("/actual/{estudianteId}/export")
	public ResponseEntity<byte[]> exportHorarioActual(
		@PathVariable("estudianteId") Long estudianteId,
		@RequestParam(name = "formato", defaultValue = "csv") String formato,
		Principal principal
	) {
		Long userId = getAuthenticatedUserId(principal);
		if (estudianteId == null || !estudianteId.equals(userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permisos para acceder a este horario");
		}

		String normalizedFormat = formato == null ? "csv" : formato.trim().toLowerCase(Locale.ROOT);
		if (!"csv".equals(normalizedFormat) && !"pdf".equals(normalizedFormat) && !FORMATO_IMAGEN.equals(normalizedFormat)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato de exportacion no soportado");
		}

		if (!horarioRecomendadoService.hasHorarioActual(userId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay horario disponible para exportar");
		}

		byte[] payload;
		if ("pdf".equals(normalizedFormat)) {
			payload = horarioRecomendadoService.buildHorarioActualPdf(userId);
		} else if (FORMATO_IMAGEN.equals(normalizedFormat)) {
			payload = horarioRecomendadoService.buildHorarioActualImage(userId);
		} else {
			payload = horarioRecomendadoService.buildHorarioActualCsv(userId);
		}
		String dateLabel = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
		String extension;
		if ("pdf".equals(normalizedFormat)) {
			extension = "pdf";
		} else if (FORMATO_IMAGEN.equals(normalizedFormat)) {
			extension = "png";
		} else {
			extension = "csv";
		}
		String filename = "horario_" + userId + "_" + dateLabel + "." + extension;

		HttpHeaders headers = new HttpHeaders();
		MediaType contentType;
		if ("pdf".equals(normalizedFormat)) {
			contentType = MediaType.APPLICATION_PDF;
		} else if (FORMATO_IMAGEN.equals(normalizedFormat)) {
			contentType = MediaType.IMAGE_PNG;
		} else {
			contentType = MediaType.parseMediaType("text/csv");
		}
		headers.setContentType(contentType);
		headers.setContentDispositionFormData("attachment", filename);
		headers.setContentLength(payload.length);

		return new ResponseEntity<>(payload, headers, HttpStatus.OK);
	}

	private Long getAuthenticatedUserId(Principal principal) {
		if (principal == null || principal.getName() == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesion invalida");
		}

		try {
			return Long.valueOf(principal.getName());
		} catch (NumberFormatException ex) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesion invalida");
		}
	}
}
