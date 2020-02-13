package es.us.isa.restest.validation.exceptions;

/**
 * REST-Assured filter to assert that the status code is lower than 500
 *
 * @author Alberto Martin-Lopez
 */
public class StatusCode5XXValidationException extends RuntimeException {
    public StatusCode5XXValidationException(String message) {
        super(message);
    }
}
