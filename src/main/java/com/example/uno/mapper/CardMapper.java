package com.example.uno.mapper;

import com.example.uno.dto.CardDTO;
import com.example.uno.model.Card;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CardMapper
{
    CardDTO toDto(Card entity);
    Card toEntity(CardDTO dto);
}