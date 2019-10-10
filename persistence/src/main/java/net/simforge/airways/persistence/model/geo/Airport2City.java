/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.persistence.model.geo;

import javax.persistence.*;

@Entity(name = "Airport2City")
@Table(name = "aw_airport2city")
public class Airport2City {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aw_airport2city_id")
    @SequenceGenerator(name = "aw_airport2city_id", sequenceName = "aw_airport2city_id_seq", allocationSize = 1)
    private Integer id;
    @Version
    private Integer version;

    @ManyToOne
    @JoinColumn(name = "airport_id")
    private Airport airport;
    @ManyToOne
    @JoinColumn(name = "city_id")
    private City city;
    @Column
    private Integer dataset;

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

    public Airport getAirport() {
        return airport;
    }

    public void setAirport(Airport airport) {
        this.airport = airport;
    }

    public City getCity() {
        return city;
    }

    public void setCity(City city) {
        this.city = city;
    }

    public Integer getDataset() {
        return dataset;
    }

    public void setDataset(Integer dataset) {
        this.dataset = dataset;
    }
}
