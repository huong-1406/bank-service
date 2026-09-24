package com.bank.bankservice.service;

import com.bank.bankservice.dto.request.CreateCardRequest;
import com.bank.bankservice.dto.response.CardResponse;

import java.util.List;

public interface CardService {

    List<CardResponse> getCards(Long accountId);

    CardResponse createCard(Long accountId, CreateCardRequest request);

    void deleteCard(Long accountId, Long cardId);
}
