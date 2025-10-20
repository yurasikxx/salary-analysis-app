INSERT INTO user_roles (name)
VALUES ('ADMIN'),
       ('HR'),
       ('RATESETTER'),
       ('ACCOUNTANT'),
       ('ANALYST')
ON CONFLICT (name) DO NOTHING;

INSERT INTO departments (name)
VALUES ('Отдел кадров'),
       ('Отдел труда и заработной платы'),
       ('Бухгалтерия'),
       ('Отдел информационных технологий'),
       ('Производственный отдел'),
       ('Отдел продаж'),
       ('Отдел аналитики')
ON CONFLICT (name) DO NOTHING;

INSERT INTO positions (title, base_salary)
VALUES ('Специалист по кадрам', 1800.00),
       ('Специалист по организации и нормированию труда', 2000.00),
       ('Главный бухгалтер', 2800.00),
       ('Бухгалтер', 2000.00),
       ('Системный администратор', 2200.00),
       ('Инженер-программист', 2400.00),
       ('Главный инженер', 3200.00),
       ('Инженер-сборщик', 1900.00),
       ('Менеджер по продажам', 1700.00),
       ('Аналитик', 2300.00),
       ('Директор', 5000.00)
ON CONFLICT (title) DO NOTHING;

INSERT INTO mark_types (code, name, description)
VALUES ('Я', 'Явка', 'Рабочий день'),
       ('ОТ', 'Отгул', 'Выходной за свой счет'),
       ('Б', 'Больничный', 'Листок нетрудоспособности'),
       ('О', 'Отпуск', 'Оплачиваемый отпуск'),
       ('С', 'Сверхурочные', 'Сверхурочная работа')
ON CONFLICT (code) DO NOTHING;

INSERT INTO payment_types (code, name, category, description)
VALUES ('SALARY', 'Оклад', 'accrual', 'Основной оклад по отработанному времени'),
       ('BONUS_ITR', 'Премия ИТР', 'accrual', 'Премия инженерно-техническим работникам'),
       ('BONUS_DEPT', 'Премия по подразделению', 'accrual', 'Премия по итогам работы подразделения'),
       ('BONUS_COMP', 'Премия по предприятию', 'accrual', 'Общая премия по предприятию'),
       ('SENIORITY', 'Надбавка за стаж', 'accrual', 'Доплата за продолжительность работы'),
       ('OVERTIME', 'Сверхурочные', 'accrual', 'Оплата сверхурочной работы'),
       ('INCOME_TAX', 'Подоходный налог', 'deduction', 'Налог на доходы физических лиц'),
       ('SOCIAL_FUND', 'Взнос в ФСЗН', 'deduction', 'Отчисления в фонд социальной защиты'),
       ('ALIMONY', 'Алименты', 'deduction', 'Удержание по исполнительному листу'),
       ('UNION_FEE', 'Профсоюзный взнос', 'deduction', 'Членский взнос в профсоюз')
ON CONFLICT (code) DO NOTHING;

INSERT INTO employees (full_name, hire_date, position_id, department_id)
VALUES ('Петрова Анна Сергеевна', '2022-03-10', 1, 1),
       ('Кочерга Людмила Сергеевна', '2021-05-05', 1, 1),

       ('Сидоров Алексей Петрович', '2021-11-15', 2, 2),
       ('Ковалева Ирина Васильевна', '2020-06-20', 2, 2),

       ('Никитина Ольга Дмитриевна', '2019-08-12', 3, 3),
       ('Иванова Екатерина Максимовна', '2023-01-25', 4, 3),

       ('Козлов Денис Андреевич', '2022-09-05', 5, 4),
       ('Смирнов Артем Викторович', '2023-03-18', 6, 4),

       ('Васильев Михаил Юрьевич', '2018-04-15', 7, 5),
       ('Павлов Сергей Николаевич', '2022-07-30', 8, 5),
       ('Федоров Андрей Игоревич', '2023-11-10', 8, 5),

       ('Морозова Татьяна Владимировна', '2023-02-14', 9, 6),

       ('Смирнов Василий Иванович', '2021-08-13', 10, 7),
       ('Шпак Юлия Викторовна', '2024-03-19', 10, 7)
ON CONFLICT DO NOTHING;

INSERT INTO users (username, password_hash, employee_id, role_id)
VALUES ('admin', '$2a$08$patHrQlLFvCDCx4bG2cqzu5Gu4G5yFo8TDWxE0yrS44EwCAJCwBFi', NULL, 1),  -- password: admin123

       ('petrova.a', '$2a$08$UgrNvXgvQVLCNY4XluD/U.1sSgcnPFAvPlCC6Foz/.CukTSu90gTu', 1, 2), -- password: user123
       ('kocherga.l', '$2a$08$PKvV2pdVji5uln504NdaLu3JGUBYrvVw/HyZ7C6UerDYwtp18sRym', 2, 2),

       ('sidorov.a', '$2a$08$3mKriBEw0zmMeFmuWL172elaIqehazjat6ckgAtZ6uXe03gnwzg42', 3, 3),
       ('kovaleva.i', '$2a$08$.AMGGZlmYJuTRfZFjRR.XOebzvgC7Xs9vtxFYUU7QdjKjhChLAU3q', 4, 3),

       ('nikitina.o', '$2a$08$W4VQBX16eb4dCF6Vk0ioM.RnFRS6/iCFN2sR1nPFamXhON7ZScRgG', 5, 4),
       ('ivanova.e', '$2a$08$mHcvQgUdtzmlbMDZqpR4GeEBJYZBVu2Qt4NdFV9l3NR112VhD9Rm2', 6, 4),

       ('smirnov.v', '$2a$08$DH3YDdOBs3IjGd24y55ageNQmcYvHGDcMdtZo2onNujLvXUs0faHi', 13, 5),
       ('shpak.y', '$2a$08$.2LSyNzH7F40y3vLWvJYf.JYZHznjegTS.yzgOBSNsjWrXjysy0pm', 14, 5)
ON CONFLICT (username) DO NOTHING;