package it.unicas.chronogram.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Catalog of activity categories. Maps the {@code activity_type} table.
 */
@Entity
@Table(name = "activity_type")
@Getter
@Setter
@NoArgsConstructor
public class ActivityType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "activity_type_id")
    private Integer id;

    @Column(nullable = false, length = 45)
    private String name;

    private String description;

    @Column(name = "is_instrumental")
    private Boolean instrumental;

    @Column(name = "is_routinary")
    private Boolean routinary;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
