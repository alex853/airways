/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.persistence.model.geo;

import javax.persistence.*;

@Entity(name = "Country")
@Table(name = "aw_country")
public class Country {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aw_country_id")
    @SequenceGenerator(name = "aw_country_id", sequenceName = "aw_country_id_seq", allocationSize = 1)
    private Integer id;
    @Version
    private Integer version;

    @Column
    private String name;
    @Column
    private String code;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Override
    public String toString() {
        return "Country { " + name + " }";
    }

}
