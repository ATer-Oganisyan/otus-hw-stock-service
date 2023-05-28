create table catalog (
id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
good_code VARCHAR(255) NOT NULL,
good_name VARCHAR(255) NOT NULL,
good_description VARCHAR(255) NOT NULL,
measurement_units VARCHAR(255) NOT NULL,
price_per_unit INT NOT NULL
);

create table stock (
id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
catalog_id INT NOT NULL,
operation_type TINYINT NOT NULL,
cnt INT NOT NULL,
order_id INT NULL,
request_id VARCHAR(255) NOT NULL UNIQUE KEY,
foreign key (catalog_id) references catalog(id)
);

create table cancelled_orders (
order_id INT NOT NULL PRIMARY KEY
);