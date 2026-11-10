CREATE TABLE accounttransaction (
                                    id BIGINT NOT NULL AUTO_INCREMENT,
                                    account_id BIGINT NOT NULL,
                                    destination_account_id BIGINT NULL,
                                    type VARCHAR(10) NOT NULL,
                                    amount DECIMAL(15, 2) NOT NULL,
                                    occurred_at DATETIME(6) NOT NULL,
                                    PRIMARY KEY (id),
                                    CONSTRAINT fk_transaction_account
                                        FOREIGN KEY (account_id) REFERENCES account (id),
                                    CONSTRAINT fk_transaction_destination
                                        FOREIGN KEY (destination_account_id) REFERENCES account (id)
) ENGINE=InnoDB;