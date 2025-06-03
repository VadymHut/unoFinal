package com.example.uno.dto;

import com.example.uno.model.ActionType;
import com.example.uno.model.Color;
import lombok.Builder;

@Builder
public record MoveDTO(ActionType type, CardDTO card, Color chosenColor) {}