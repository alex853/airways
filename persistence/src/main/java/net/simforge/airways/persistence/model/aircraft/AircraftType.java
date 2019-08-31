/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.persistence.model.aircraft;

import net.simforge.commons.hibernate.BaseEntity;

import javax.persistence.*;

@Entity(name = "AircraftType")
@Table(name = "aw_aircraft_type")
public class AircraftType implements BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aw_aircraft_type_id")
    @SequenceGenerator(name = "aw_aircraft_type_id", sequenceName = "aw_aircraft_type_id_seq", allocationSize = 1)
    private Integer id;
    @Version
    private Integer version;

    @Column
    private String icao;
    @Column
    private String iata;
    @Column(name = "typical_cruise_altitude")
    private Integer typicalCruiseAltitude;
    @Column(name = "typical_cruise_speed")
    private Integer typicalCruiseSpeed;
    @Column(name = "climb_vertical_speed")
    private Integer climbVerticalSpeed;
    @Column(name = "descent_vertical_speed")
    private Integer descentVerticalSpeed;
    @Column(name = "takeoff_speed")
    private Integer takeoffSpeed;
    @Column(name = "landing_speed")
    private Integer landingSpeed;

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

    public String getIcao() {
        return icao;
    }

    public void setIcao(String icao) {
        this.icao = icao;
    }

    public String getIata() {
        return iata;
    }

    public void setIata(String iata) {
        this.iata = iata;
    }

    public Integer getTypicalCruiseAltitude() {
        return typicalCruiseAltitude;
    }

    public void setTypicalCruiseAltitude(Integer typicalCruiseAltitude) {
        this.typicalCruiseAltitude = typicalCruiseAltitude;
    }

    public Integer getTypicalCruiseSpeed() {
        return typicalCruiseSpeed;
    }

    public void setTypicalCruiseSpeed(Integer typicalCruiseSpeed) {
        this.typicalCruiseSpeed = typicalCruiseSpeed;
    }

    public Integer getClimbVerticalSpeed() {
        return climbVerticalSpeed;
    }

    public void setClimbVerticalSpeed(Integer climbVerticalSpeed) {
        this.climbVerticalSpeed = climbVerticalSpeed;
    }

    public Integer getDescentVerticalSpeed() {
        return descentVerticalSpeed;
    }

    public void setDescentVerticalSpeed(Integer descentVerticalSpeed) {
        this.descentVerticalSpeed = descentVerticalSpeed;
    }

    public Integer getTakeoffSpeed() {
        return takeoffSpeed;
    }

    public void setTakeoffSpeed(Integer takeoffSpeed) {
        this.takeoffSpeed = takeoffSpeed;
    }

    public Integer getLandingSpeed() {
        return landingSpeed;
    }

    public void setLandingSpeed(Integer landingSpeed) {
        this.landingSpeed = landingSpeed;
    }

    @Override
    public String toString() {
        return "AircraftType{" +
                "id=" + id +
                ", icao='" + icao + '\'' +
                '}';
    }
}
