package com.glovishedge.ai.dto;

import java.time.LocalDate;

public record CompareRoutesContext(String incoterm, String unit, LocalDate deadline) {
}
