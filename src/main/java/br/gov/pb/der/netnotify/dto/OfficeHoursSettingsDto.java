package br.gov.pb.der.netnotify.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OfficeHoursSettingsDto {

    @JsonProperty("availabilityWindows")
    @JsonAlias("availability_windows")
    private String availabilityWindows;
}
