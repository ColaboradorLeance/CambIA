package com.cambia.web;

import java.util.stream.Collectors;

import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JacksonException;

/**
 * Deixa TODO corpo de erro (validação, JSON malformado, tipo de campo errado, registro em
 * uso) explicando exatamente o que aconteceu — em vez das mensagens genéricas que o Spring
 * devolve por padrão ("Invalid request content.", "Failed to read request", "Internal
 * Server Error") — para o front-end conseguir mostrar um pop-up com a causa real do erro.
 */
@ControllerAdvice
class TratamentoErroGlobal extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(TratamentoErroGlobal.class);

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String detalhe = ex.getBindingResult().getFieldErrors().stream()
				.map(erro -> erro.getField() + ": " + erro.getDefaultMessage())
				.collect(Collectors.joining("; "));
		return comProblemDetail(ex, status, detalhe, headers, request);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String detalhe = "Corpo da requisição inválido: " + causaOriginal(ex);
		return comProblemDetail(ex, status, detalhe, headers, request);
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		String campo = ex instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException matEx
				? matEx.getName()
				: "valor";
		Class<?> tipo = ex.getRequiredType();
		String detalhe = "%s: \"%s\" inválido, esperado %s".formatted(campo, ex.getValue(), descreverTipo(tipo));
		return comProblemDetail(ex, status, detalhe, headers, request);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<Object> tratarViolacaoIntegridade(DataIntegrityViolationException ex, WebRequest request) {
		String detalhe = "Não é possível concluir esta ação: o registro está em uso por outro cadastro do sistema "
				+ "(ex.: operações, sessões de login ou fechamentos já vinculados a ele).";
		return comProblemDetail(ex, HttpStatus.CONFLICT, detalhe, new HttpHeaders(), request);
	}

	// Último recurso: qualquer exceção que nenhum @ExceptionHandler específico tratou (um
	// bug de verdade, não um erro de negócio esperado). A stack trace completa vai pro log
	// do servidor — a mensagem original da exceção NUNCA vai pro cliente (pode conter
	// detalhe interno: caminho de arquivo, host de banco, fragmento de SQL, etc. —
	// achado de revisão de segurança). Só uma mensagem fixa e genérica é devolvida.
	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> tratarErroInesperado(Exception ex, WebRequest request) {
		log.error("Erro inesperado não tratado especificamente", ex);
		String detalhe = "Erro inesperado no servidor. Tente novamente ou contate o suporte.";
		return comProblemDetail(ex, HttpStatus.INTERNAL_SERVER_ERROR, detalhe, new HttpHeaders(), request);
	}

	private ResponseEntity<Object> comProblemDetail(Exception ex, HttpStatusCode status, String detalhe,
			HttpHeaders headers, WebRequest request) {
		ProblemDetail corpo = ProblemDetail.forStatusAndDetail(status, detalhe);
		return handleExceptionInternal(ex, corpo, headers, status, request);
	}

	// Pra enum, lista os valores aceitos em vez de só o nome da classe Java ("Periodo") —
	// bem mais útil pra quem está montando a chamada (ou lendo o pop-up de erro).
	private String descreverTipo(Class<?> tipo) {
		if (tipo == null) {
			return "outro formato";
		}
		if (tipo.isEnum()) {
			String valores = java.util.Arrays.stream(tipo.getEnumConstants())
					.map(Object::toString)
					.collect(Collectors.joining(", "));
			return "um de: " + valores;
		}
		return tipo.getSimpleName();
	}

	private String causaOriginal(HttpMessageNotReadableException ex) {
		Throwable causa = ex.getCause();
		if (causa instanceof JacksonException jacksonEx) {
			return jacksonEx.getOriginalMessage();
		}
		return "verifique se o JSON enviado está bem formado";
	}

}
