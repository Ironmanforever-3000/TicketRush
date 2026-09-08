package com.ticketrush.venue;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "venues")
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(
        name = "layout_json",
        nullable = false,
        columnDefinition = "jsonb"
    )
    @JdbcTypeCode(SqlTypes.JSON)
    private String layoutJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Venue() {
    }

    public Venue(
            String name,
            String city,
            String layoutJson,
            OffsetDateTime createdAt
    ) {
        this.name = name;
        this.city = city;
        this.layoutJson = layoutJson;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCity() {
        return city;
    }

    public String getLayoutJson() {
        return layoutJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public void setLayoutJson(String layoutJson) {
        this.layoutJson = layoutJson;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

