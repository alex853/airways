package net.simforge.airways.entities.geo;

import net.simforge.airways.model.geo.Airport;
import net.simforge.commons.hibernate.BaseEntity;

import javax.persistence.*;

@Deprecated
@Entity(name = "Airport")
@Table(name = "aw_airport")
public class AirportEntity implements Airport, BaseEntity/*, EventLog.Loggable*/ {
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

    //@Override
    public String getEventLogCode() {
        return EventLogCode;
    }

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    @Override
    public Integer getVersion() {
        return version;
    }

    @Override
    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String getIata() {
        return iata;
    }

    @Override
    public void setIata(String iata) {
        this.iata = iata;
    }

    @Override
    public String getIcao() {
        return icao;
    }

    @Override
    public void setIcao(String icao) {
        this.icao = icao;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public Double getLatitude() {
        return latitude;
    }

    @Override
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    @Override
    public Double getLongitude() {
        return longitude;
    }

    @Override
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    @Override
    public Integer getDataset() {
        return dataset;
    }

    @Override
    public void setDataset(Integer dataset) {
        this.dataset = dataset;
    }

    @Override
    public String toString() {
        return "Airport { icao " + icao + " }";
    }
}
