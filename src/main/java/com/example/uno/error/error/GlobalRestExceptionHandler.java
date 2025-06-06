package com.example.uno.error;

import com.example.uno.dto.ErrorDTO;
import com.example.uno.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestControllerAdvice
public class GlobalRestExceptionHandler {


    @ExceptionHandler(GameFullException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorDTO gameFull(GameFullException ex)
    {
        return ErrorDTO.of(409, "Game is already full");
    }

    @ExceptionHandler(NotYourTurnException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorDTO notYourTurn(NotYourTurnException ex)
    {
        return ErrorDTO.of(403, "It is not your turn");
    }

    @ExceptionHandler(IllegalMoveException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDTO illegalMove(IllegalMoveException ex)
    {
        return ErrorDTO.of(400, ex.getMessage());
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorDTO invalidBody(MethodArgumentNotValidException ex)
    {
        var first = ex.getBindingResult().getFieldError();
        String msg = (first == null) ? "Invalid request body"
                : first.getField() + " " + first.getDefaultMessage();
        return ErrorDTO.of(400, msg);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorDTO unhandled(Exception ex, HttpServletRequest req)
    {
        log.error("Unhandled exception at {} {}", req.getMethod(), req.getRequestURI(), ex);
        return ErrorDTO.of(500, "Unexpected server error");
    }
}
