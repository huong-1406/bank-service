package com.bank.bankservice.service.impl;

import com.bank.bankservice.dto.request.CreateCardRequest;
import com.bank.bankservice.dto.response.CardResponse;
import com.bank.bankservice.entity.Account;
import com.bank.bankservice.entity.Card;
import com.bank.bankservice.entity.enums.TransactionStatus;
import com.bank.bankservice.exception.BusinessException;
import com.bank.bankservice.exception.ErrorCode;
import com.bank.bankservice.repository.AccountRepository;
import com.bank.bankservice.repository.CardRepository;
import com.bank.bankservice.repository.TransactionRepository;
import com.bank.bankservice.service.CardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CardServiceImpl implements CardService {

    private final AccountRepository accountRepository;
    private final CardRepository cardRepository;
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CardResponse> getCards(Long accountId) {
        // Không có thẻ thì trả danh sách rỗng, không phải 404
        return cardRepository.findByAccountId(accountId).stream()
                .map(CardResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public CardResponse createCard(Long accountId, CreateCardRequest request) {
        // Đề bài: không được tạo thẻ cho tài khoản không tồn tại
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        Card card = new Card();
        card.setAccount(account);
        card.setCardType(request.cardType());
        card.setExpiryDate(request.expiryDate());
        return CardResponse.from(cardRepository.save(card));
    }

    @Override
    @Transactional
    public void deleteCard(Long accountId, Long cardId) {
        // Thẻ của người khác coi như không tồn tại → 404, không lộ là id đó có thật
        Card card = cardRepository.findByIdAndAccountId(cardId, accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));

        // Đề bài: chỉ chặn khi còn giao dịch đang chờ xử lý
        if (transactionRepository.existsByCardIdAndStatus(cardId, TransactionStatus.PENDING)) {
            throw new BusinessException(ErrorCode.CARD_HAS_PENDING_TRANSACTION);
        }

        // Giao dịch cũ của thẻ bị xoá theo nhờ ON DELETE CASCADE
        cardRepository.delete(card);
    }
}
