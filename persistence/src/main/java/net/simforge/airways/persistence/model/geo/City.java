/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.persistence.model.geo;

import net.simforge.airways.persistence.EventLog;
import net.simforge.commons.misc.Geo;

import javax.persistence.*;

@Entity(name = "City")
@Table(name = "aw_city")
public class City implements EventLog.Loggable {
    public static final String EventLogCode = "city";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aw_city_id")
    @SequenceGenerator(name = "aw_city_id", sequenceName = "aw_city_id_seq", allocationSize = 1)
    private Integer id;
    @Version
    private Integer version;

    @ManyToOne
    @JoinColumn(name = "country_id")
    private Country country;
    @Column
    private String name;
    @Column
    private Double latitude;
    @Column
    private Double longitude;
    @Column
    private Integer population;
    @Column
    private Integer dataset;

    @Override
    public String getEventLogCode() {
        return EventLogCode;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Country getCountry() {
        return country;
    }

    public void setCountry(Country country) {
        this.country = country;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Geo.Coords getCoords() {
        return new Geo.Coords(latitude, longitude);
    }

    public Integer getPopulation() {
        return population;
    }

    public void setPopulation(Integer population) {
        this.population = population;
    }

    public Integer getDataset() {
        return dataset;
    }

    public void setDataset(Integer dataset) {
        this.dataset = dataset;
    }

    @Override
    public String toString() {
        return "City { " + name + ", population " + population + " }";
    }

    public String getCityWithCountryName() {
        return name + ", " + getCountry().getName();
    }
}
