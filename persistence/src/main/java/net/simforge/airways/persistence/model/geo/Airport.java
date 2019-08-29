package net.simforge.airways.persistence.model.geo;

import javax.persistence.*;

@Entity(name = "Airport")
@Table(name="aw_airport")
public class Airport {
    public static final String EventLogCode = "airport";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aw_airport_id_seq")
    @SequenceGenerator(name = "aw_airport_id_seq", sequenceName = "aw_airport_id_seq", allocationSize = 1)
    private Integer id;
    @Version
    private Integer version;

    @Column
    private String iata;
    @Column
    private String icao;
    @Column
    private String name;
    @Column
    private Double latitude;
    @Column
    private Double longitude;
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

    public String getIata() {
        return iata;
    }

    public void setIata(String iata) {
        this.iata = iata;
    }

    public String getIcao() {
        return icao;
    }

    public void setIcao(String icao) {
        this.icao = icao;
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

    public Integer getDataset() {
        return dataset;
    }

    public void setDataset(Integer dataset) {
        this.dataset = dataset;
    }

    @Override
    public String toString() {
        return "Airport { icao " + icao + " }";
    }
}
