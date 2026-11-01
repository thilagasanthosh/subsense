package com.subsense.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "category")
public class Category {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String name;

  @Column(nullable = false)
  private String colour;

  protected Category() {}

  public Category(String name, String colour) {
    this.name = name;
    this.colour = colour;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getColour() {
    return colour;
  }
}
