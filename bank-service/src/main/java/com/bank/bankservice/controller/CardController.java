package com.bank.bankservice.controller;

import com.bank.bankservice.dto.request.CreateCardRequest;
import com.bank.bankservice.dto.response.CardResponse;
import com.bank.bankservice.security.SecurityUtil;
import com.bank.bankservice.service.CardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

    @GetMapping("/accounts/me/cards")
    public List<CardResponse> getMyCards() {
        return cardService.getCards(SecurityUtil.currentAccountId());
    }

    @PostMapping("/accounts/me/cards")
    @ResponseStatus(HttpStatus.CREATED)
    public CardResponse createCard(@Valid @RequestBody CreateCardRequest request) {
        return cardService.createCard(SecurityUtil.currentAccountId(), request);
    }

    // API duy nhất có id trên URL; Service kiểm tra thẻ có thuộc người đang đăng nhập không
    @DeleteMapping("/cards/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCard(@PathVariable Long id) {
        cardService.deleteCard(SecurityUtil.currentAccountId(), id);
    }
}
