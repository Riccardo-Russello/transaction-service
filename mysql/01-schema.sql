CREATE TABLE person (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        first_name VARCHAR(30) NOT NULL,
                        last_name VARCHAR(30) NOT NULL,
                        date_of_birth DATE NOT NULL,
                        username VARCHAR(50) NOT NULL,
                        password VARCHAR(100) NOT NULL,
                        role ENUM('admin', 'user') NOT NULL,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_person_username (username)
) ENGINE=InnoDB;

CREATE TABLE account (
                         id BIGINT NOT NULL AUTO_INCREMENT,
                         person_id BIGINT NOT NULL,
                         account_number VARCHAR(30) NOT NULL,
                         opened_at DATETIME NOT NULL,
                         balance DECIMAL(15, 2) NOT NULL,
                         PRIMARY KEY (id),
                         UNIQUE KEY uk_account_number (account_number),
                         CONSTRAINT fk_account_person
                             FOREIGN KEY (person_id) REFERENCES person (id)
) ENGINE=InnoDB;

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