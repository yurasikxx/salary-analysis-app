package by.bsuir.saa.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "mark_types")
public class MarkType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "code", nullable = false, unique = true, length = 10)
    private String code;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "description")
    private String description;
}