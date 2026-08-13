package com.glovishedge.rag.dto;

import java.util.List;

public record RagAskResult(String answer, List<Integer> used, String caveat) {
}
