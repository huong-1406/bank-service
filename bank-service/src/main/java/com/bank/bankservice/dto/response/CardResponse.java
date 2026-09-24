package com.bank.bankservice.dto.response;

import com.bank.bankservice.entity.Card;
import com.bank.bankservice.entity.enums.CardStatus;
import com.bank.bankservice.entity.enums.CardType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CardResponse(
        Long cardId,
        CardType cardType,
        LocalDate expiryDate,
        CardStatus status,
        LocalDateTime createdAt
) {
    public static CardResponse from(Card card) {
        return new CardResponse(card.getId(), card.getCardType(), card.getExpiryDate(),
                card.getStatus(), card.getCreatedAt());
    }
}
