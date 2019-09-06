/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.legacy.airways.aircraft;

import net.simforge.commons.persistence.BaseEntity;
import net.simforge.commons.persistence.Column;
import net.simforge.commons.persistence.Table;

@Table(name="ac_model")
public class Model extends BaseEntity {
    @Column
    private String name;

    @Column
    private int maxSeatingCapacity;

    @Column
    private int maxCruisingSpeed;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getMaxSeatingCapacity() {
        return maxSeatingCapacity;
    }

    public void setMaxSeatingCapacity(int maxSeatingCapacity) {
        this.maxSeatingCapacity = maxSeatingCapacity;
    }

    public int getMaxCruisingSpeed() {
        return maxCruisingSpeed;
    }

    public void setMaxCruisingSpeed(int maxCruisingSpeed) {
        this.maxCruisingSpeed = maxCruisingSpeed;
    }
}
