/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.persistence.model.geo;

import net.simforge.airways.persistence.EventLog;
import net.simforge.commons.hibernate.Auditable;
import net.simforge.commons.hibernate.BaseEntity;
import net.simforge.commons.misc.Geo;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity(name = "Airport")
@Table(name="aw_airport")
public class Airport implements BaseEntity, EventLog.Loggable, Auditable {
    public static final String EventLogCode = "airport";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aw_airport_id")
    @SequenceGenerator(name = "aw_airport_id", sequenceName = "aw_airport_id_seq", allocationSize = 1)
    private Integer id;
    @Version
    private Integer version;

    @SuppressWarnings("unused")
    @Column(name = "create_dt")
    private LocalDateTime createDt;
    @SuppressWarnings("unused")
    @Column(name = "modify_dt")
    private LocalDateTime modifyDt;

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

    @Override
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
    public LocalDateTime getCreateDt() {
        return createDt;
    }

    @Override
    public LocalDateTime getModifyDt() {
        return modifyDt;
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

    public Geo.Coords getCoords() {
        return new Geo.Coords(latitude, longitude);
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
