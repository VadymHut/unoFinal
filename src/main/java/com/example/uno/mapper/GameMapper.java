package com.example.uno.mapper;

import com.example.uno.dto.CreateGameRequest;
import com.example.uno.model.UnoGame;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface GameMapper
{

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "gameCode", ignore = true)
    @Mapping(target = "status", constant = "WAITING")
    @Mapping(target = "players", ignore = true)
    UnoGame requestToGame(CreateGameRequest request);
}