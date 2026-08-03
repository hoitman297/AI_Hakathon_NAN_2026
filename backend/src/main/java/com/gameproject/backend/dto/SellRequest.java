package com.gameproject.backend.dto;

/** itemType: "CROP" 또는 "FRUIT" */
public record SellRequest(String itemType, Long itemRefId, Integer quantity) {
}
