package com.example.account.repository;

import com.example.account.entity.Account;
import com.example.account.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Account findByWallet(Wallet wallet);
}
