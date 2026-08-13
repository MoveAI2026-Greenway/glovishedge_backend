package com.glovishedge.rag.embedding;

public class EmbeddingClientException extends RuntimeException {

    public EmbeddingClientException(String message) {
        super(message);
    }

    public EmbeddingClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
