package com.example.account.service;

import com.example.account.entity.Account;
import com.example.account.entity.Wallet;
import com.example.account.model.AccountRequest;
import com.example.account.model.AccountResponse;
import com.example.account.repository.AccountRepository;
import com.example.account.repository.WalletRepository;
import com.example.amqp.RabbitMQMessageProducer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@AllArgsConstructor
public class AccountServiceImpl implements AccountService{

    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;
//    private RestTemplate restTemplate;
    private RabbitMQMessageProducer rabbitMQMessageProducer;

    @Override
    public ResponseEntity<String> createAccount(AccountRequest accountRequest) {

        if(accountRepository.existsByEmail(accountRequest.getEmail())){
            throw new RuntimeException("Account already exist");
        }
        
        Account account = Account.builder()
                .firstName(accountRequest.getFirstName())
                .lastName(accountRequest.getLastName())
                .email(accountRequest.getEmail())
                .password(accountRequest.getPassword())
                .phoneNumber(accountRequest.getPhoneNumber())
                .address(accountRequest.getAddress())
                .wallet(buildWallet(accountRequest))
                .createdAt(LocalDateTime.now())
                .modifiedAt(LocalDateTime.now())
                .build();

        Account savedAccount = accountRepository.save(account);


        Account account1 = Account.builder()
                .email(savedAccount.getEmail())
                .firstName(savedAccount.getFirstName())
                .id(savedAccount.getId())
                .build();

//        String url = "http://NOTIFICATION-SERVICE/api/v1/notification/verify";
//        restTemplate.postForObject(url, account1, void.class);

        rabbitMQMessageProducer.publish(account1, "internal.notification.routing-key",
                "internal.exchange");

        return new ResponseEntity<>("Account created", HttpStatus.CREATED);
    }

    public Wallet buildWallet(AccountRequest accountRequest) {

        Wallet wallet = Wallet.builder()
                .accountNumber(accountNumber())
                .bvn(accountRequest.getBvn())
                .pin(accountRequest.getPin())
                .build();

        log.info("Account number generated {}",wallet.getAccountNumber());
        return walletRepository.save(wallet);
    }


    //account number generator
    public String accountNumber() {
        String uuid = UUID.randomUUID().toString().replace("-", "");

        String numbers = "1234567890";
        for (int i = 0; i < uuid.length(); i++) {
            if (!numbers.contains(uuid.charAt(i) + "")) {
                uuid = uuid.replace(uuid.charAt(i) + "", "");
            }
            log.info(uuid.substring(0, 10));
        }
        return uuid.substring(0, 10);
    }



    @Override
    public void enableAccount(Long account_id) {
        Account account = accountRepository.findById(account_id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, String.format("account with id %s not found",account_id)));
        account.setEnabled(true);
        accountRepository.save(account);
    }


    @Override
    public ResponseEntity<String> updateAccount(Map<String, Object> accountRequest, Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, String.format("account with id %s not found",id)));

        accountRequest.forEach((k,v) -> {
            Field field = ReflectionUtils.findField(Account.class, k);
            assert field != null;
            field.setAccessible(true);
            ReflectionUtils.setField(field, account, v);
        });

        accountRepository.save(account);

        return new ResponseEntity<>("Account updated", HttpStatus.OK);
    }


    @Override
    public ResponseEntity<AccountResponse> viewAccountByAcctHolder(Long id) {
        Account account = accountRepository.findById(id).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, String.format("account with id %s not found",id)));
        AccountResponse accountResponse = AccountResponse.builder()
                .firstName(account.getFirstName())
                .lastName(account.getLastName())
                .email(account.getEmail())
                .phoneNumber(account.getPhoneNumber())
                .address(account.getAddress())
                .accountNumber(account.getWallet().getAccountNumber())
                .bvn(account.getWallet().getBvn())
                .build();

        return new ResponseEntity<>(accountResponse, HttpStatus.OK);
    }



    @Override
    public ResponseEntity<Account> getAccountWithAccountNum(String accountNum) {
        Optional<Wallet> wallet = Optional.ofNullable(walletRepository.findByAccountNumber(accountNum));
        Account account = null;
        if (wallet.isPresent()) {
            account = accountRepository.findByWallet(wallet.get());
        }

        return new ResponseEntity<>(account,HttpStatus.OK);
    }


    @Override
    public ResponseEntity<String> accountUpdate(Account account, Long id) {
        Account acct = accountRepository.findById(id).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, String.format("account with id %s not found",id)));
        BeanUtils.copyProperties(account, acct);
        log.info(String.valueOf(account.getWallet().getBalance()));

        walletRepository.save(account.getWallet());
        accountRepository.save(acct);

        return new ResponseEntity<>("account updated by transaction",HttpStatus.OK);
    }


    @Override
    public ResponseEntity<String> deleteAccount(Long id) {
        Account account = accountRepository.findById(id).
                orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, String.format("account with id %s not found",id)));

        accountRepository.delete(account);

        return new ResponseEntity<>("Account deleted", HttpStatus.OK);
    }

}
