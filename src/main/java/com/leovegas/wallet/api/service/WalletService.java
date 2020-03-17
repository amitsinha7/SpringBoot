package com.leovegas.wallet.api.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;

import javax.naming.InsufficientResourcesException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.leovegas.wallet.api.constant.WalletConstant;
import com.leovegas.wallet.api.entity.Account;
import com.leovegas.wallet.api.entity.Player;
import com.leovegas.wallet.api.entity.Transaction;
import com.leovegas.wallet.api.exception.WalletException;
import com.leovegas.wallet.api.repository.AccountRepository;
import com.leovegas.wallet.api.repository.PlayerRepository;
import com.leovegas.wallet.api.repository.TransactionRepository;
import com.leovegas.wallet.api.request.dto.TransactionRequest;
import com.leovegas.wallet.api.response.dto.AccountDTO;
import com.leovegas.wallet.api.response.dto.PlayerDTO;
import com.leovegas.wallet.api.response.dto.TransactionDTO;
import com.leovegas.wallet.api.response.dto.WalletResponseDTO;

@Service
@Transactional
public class WalletService {

	@Autowired
	PlayerRepository playerRepository;

	@Autowired
	TransactionRepository transactionRepository;

	@Autowired
	AccountRepository accountRepository;

	public WalletResponseDTO findPlayerById(Long id) throws WalletException {
		Optional<Player> player = playerRepository.findById(id);
		PlayerDTO playerDTO = null;
		if (player.isPresent()) {
			playerDTO = convertPlayerDomainToPlayerDTO(player.get());
		} else {
			throw new WalletException();
		}
		return convertWalletResponseDTOFromPlayerDTO(playerDTO);
	}

	@Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.SERIALIZABLE, rollbackFor = WalletException.class)
	public WalletResponseDTO accountTransactions(Long accountId, TransactionRequest transactionRequest)
			throws WalletException, InsufficientResourcesException {
		Optional<Account> account = accountRepository.findById(accountId);
		AccountDTO accountDTO = null;
		Transaction createdTransaction = null;
		if (account.isPresent()) {
			long sum = 0;
			boolean transactionIdValid = account.get().getTransactions().stream().allMatch(transaction -> transaction
					.getAccountTransactionId() == transactionRequest.getAccountTransactionId());
			if (transactionIdValid) {
				if (transactionRequest.getTransactionType().equalsIgnoreCase(WalletConstant.CREDIT)) {
					sum = Long.sum(account.get().getBalance(), transactionRequest.getAmount());
					createdTransaction = createTransactionRequest(transactionRequest, account.get());
				} else {
					if (Long.sum(account.get().getBalance(), -(transactionRequest.getAmount())) >= 0) {
						createdTransaction = createTransactionRequest(transactionRequest, account.get());
					} else {
						throw new InsufficientResourcesException("Insufficient Fund To Operate");
					}
				}
			}
			account.get().setBalance(sum);
			account.get().addTransaction(createdTransaction);
			try {
				Account savedAccount = accountRepository.save(account.get());
				createdTransaction.setStatus(WalletConstant.SUCCESS);
				createdTransaction.setCreatedDate(new Date());
				transactionRepository.save(createdTransaction);
				savedAccount.addTransaction(createdTransaction);
				accountDTO = convertAccountDomainToAccountDTO(savedAccount);
				
			} catch (Exception e) {
				createdTransaction.setStatus(WalletConstant.FAILED);
				createdTransaction.setCreatedDate(new Date());
				transactionRepository.save(createdTransaction);
				throw new WalletException();
			}

		} else {
			throw new WalletException();
		}
		return convertWalletResponseDTOFromAccountDTO(accountDTO);
	}

	public Player createPlayer(Player player) {
		return playerRepository.save(player);
	}

	public Account createAccount(Account account) throws Exception {
		if (account != null && account.getPlayer() != null && account.getPlayer().getId() != null) {

			Optional<Player> player = playerRepository.findById(account.getPlayer().getId());
			if (player.isPresent()) {
				throw new Exception();
			} else {
				return accountRepository.save(account);
			}
		} else {
			throw new Exception();
		}
	}

	private Transaction createTransactionRequest(TransactionRequest transactionRequest, Account account) {
		Transaction transaction = new Transaction();
		transaction.setAmount(transactionRequest.getAmount());
		transaction.setType(transactionRequest.getTransactionType());
		transaction.setAccountTransactionId(transactionRequest.getAccountTransactionId());
		transaction.setReference(transactionRequest.getReference());
		transaction.setAccount(account);
		transaction.setPlayer(account.getPlayer());
		return transaction;
	}

	private WalletResponseDTO convertWalletResponseDTOFromPlayerDTO(PlayerDTO playerDTO) {
		WalletResponseDTO walletResponseDTO = new WalletResponseDTO();
		walletResponseDTO.setPlayerDTO(playerDTO);
		walletResponseDTO.setAccountDTO(playerDTO.getAccount());
		walletResponseDTO.setTransactionDTOs(playerDTO.getTransactions());
		return walletResponseDTO;
	}

	private WalletResponseDTO convertWalletResponseDTOFromAccountDTO(AccountDTO accountDTO) {
		WalletResponseDTO walletResponseDTO = new WalletResponseDTO();
		walletResponseDTO.setAccountDTO(accountDTO);
		return walletResponseDTO;
	}

	private PlayerDTO convertPlayerDomainToPlayerDTO(Player player) {
		PlayerDTO playerDTO = new PlayerDTO();
		playerDTO.setId(player.getId());
		playerDTO.setName(player.getName());
		playerDTO.setSex(player.getSex());
		playerDTO.setCreatedDate(player.getCreatedDate());
		playerDTO.setAccount(convertAccountDomainToAccountDTO(player.getAccount()));
		return playerDTO;
	}

	private AccountDTO convertAccountDomainToAccountDTO(Account account) {
		AccountDTO accountDTO = new AccountDTO();
		accountDTO.setAmount(account.getBalance());
		accountDTO.setId(account.getId());
		accountDTO.setCreatedDate(account.getCreatedDate());
		ArrayList<TransactionDTO> transactionDTOs = new ArrayList<TransactionDTO>();
		for (Transaction transaction : account.getTransactions()) {
			transactionDTOs.add(convertTransactionDomainToTransactionDTO(transaction));
		}
		accountDTO.setTransactions(transactionDTOs);
		return accountDTO;
	}

	private TransactionDTO convertTransactionDomainToTransactionDTO(Transaction transaction) {
		TransactionDTO transactionDTO = new TransactionDTO();
		transactionDTO.setAmount(transaction.getAmount());
		transactionDTO.setId(transaction.getId());
		transactionDTO.setCreatedDate(transaction.getCreatedDate());
		transactionDTO.setStatus(transaction.getStatus());
		transactionDTO.setType(transaction.getType());
		transactionDTO.setAccountTransactionId(transaction.getAccountTransactionId());
		return transactionDTO;
	}

}
