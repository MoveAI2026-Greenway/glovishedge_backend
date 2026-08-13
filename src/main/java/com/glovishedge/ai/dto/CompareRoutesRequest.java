package com.glovishedge.ai.dto;

import java.util.List;

public record CompareRoutesRequest(List<RouteSnapshot> routes, CompareRoutesContext context) {
}
