package by.bsuir.saa.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "payment_types")
public class PaymentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "category", nullable = false, length = 20)
    private String category;

    @Column(name = "description")
    private String description;

    @Column(name = "formula")
    private String formula;
}