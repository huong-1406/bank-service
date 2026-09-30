package com.bank.bankservice.service.impl;

import com.bank.bankservice.dto.request.CreateCardRequest;
import com.bank.bankservice.dto.response.CardResponse;
import com.bank.bankservice.entity.Account;
import com.bank.bankservice.entity.Card;
import com.bank.bankservice.entity.enums.CardStatus;
import com.bank.bankservice.entity.enums.CardType;
import com.bank.bankservice.entity.enums.TransactionStatus;
import com.bank.bankservice.exception.ErrorCode;
import com.bank.bankservice.repository.AccountRepository;
import com.bank.bankservice.repository.CardRepository;
import com.bank.bankservice.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// API 6, 7, 8: xem, tạo, xoá thẻ
@ExtendWith(MockitoExtension.class)
class CardServiceImplTest {

    @Mock private AccountRepository accountRepository;
    @Mock private CardRepository cardRepository;
    @Mock private TransactionRepository transactionRepository;
    @InjectMocks private CardServiceImpl cardService;

    private Card card() {
        Card card = new Card();
        card.setId(15L);
        card.setCardType(CardType.DEBIT);
        card.setExpiryDate(LocalDate.of(2031, 12, 31));
        card.setStatus(CardStatus.ACTIVE);
        return card;
    }

    // ===== API 6: danh sách thẻ =====
    @Test
    @DisplayName("API 6 ✅ Có thẻ → trả danh sách thẻ")
    void getCards_success() {
        when(cardRepository.findByAccountId(1L)).thenReturn(List.of(card()));

        List<CardResponse> cards = cardService.getCards(1L);

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).cardId()).isEqualTo(15L);
    }

    @Test
    @DisplayName("API 6 ❌ Chưa có thẻ → trả danh sách rỗng, không báo lỗi")
    void getCards_empty() {
        when(cardRepository.findByAccountId(1L)).thenReturn(List.of());

        assertThat(cardService.getCards(1L)).isEmpty();
    }

    // ===== API 7: tạo thẻ =====
    @Test
    @DisplayName("API 7 ✅ Tạo thẻ cho tài khoản tồn tại → thẻ mới ACTIVE")
    void createCard_success() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(new Account()));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        CardResponse response = cardService.createCard(1L, new CreateCardRequest(CardType.DEBIT, LocalDate.of(2031, 12, 31)));

        assertThat(response.status()).isEqualTo(CardStatus.ACTIVE);
        assertThat(response.cardType()).isEqualTo(CardType.DEBIT);
    }

    @Test
    @DisplayName("API 7 ❌ Tài khoản không tồn tại → ACCOUNT_NOT_FOUND, không tạo thẻ")
    void createCard_accountNotFound() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.createCard(99L, new CreateCardRequest(CardType.DEBIT, LocalDate.of(2031, 12, 31))))
                .extracting("errorCode").isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
        verify(cardRepository, never()).save(any());
    }

    // ===== API 8: xoá thẻ =====
    @Test
    @DisplayName("API 8 ✅ Xoá thẻ không còn giao dịch PENDING")
    void deleteCard_success() {
        Card card = card();
        when(cardRepository.findByIdAndAccountId(15L, 1L)).thenReturn(Optional.of(card));
        when(transactionRepository.existsByCardIdAndStatus(15L, TransactionStatus.PENDING)).thenReturn(false);

        cardService.deleteCard(1L, 15L);

        verify(cardRepository).delete(card);
    }

    @Test
    @DisplayName("API 8 ❌ Thẻ còn giao dịch PENDING → CARD_HAS_PENDING_TRANSACTION, không xoá")
    void deleteCard_hasPendingTransaction() {
        when(cardRepository.findByIdAndAccountId(15L, 1L)).thenReturn(Optional.of(card()));
        when(transactionRepository.existsByCardIdAndStatus(15L, TransactionStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> cardService.deleteCard(1L, 15L))
                .extracting("errorCode").isEqualTo(ErrorCode.CARD_HAS_PENDING_TRANSACTION);
        verify(cardRepository, never()).delete(any());
    }
}
