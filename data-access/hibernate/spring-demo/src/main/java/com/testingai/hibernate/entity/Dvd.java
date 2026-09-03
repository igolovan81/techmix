package com.testingai.hibernate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DiscriminatorValue("DVD")
@Getter
@Setter
@NoArgsConstructor
public final class Dvd extends LibraryItem {

	@Column(name = "region_code")
	private String regionCode;

	@Column(name = "runtime_minutes")
	private Integer runtimeMinutes;
}
