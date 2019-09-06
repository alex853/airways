/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.legacy.airways.aircraft;

import net.simforge.commons.persistence.BaseEntity;
import net.simforge.commons.persistence.Column;
import net.simforge.commons.persistence.Table;

@Table(name="ac_model_payload_to_range")
public class ModelPayloadToRange extends BaseEntity {
    @Column
    private int modelId;

    @Column
    private int p1Payload;

    @Column
    private int p1Range;

    @Column
    private int p2Payload;

    @Column
    private int p2Range;

    @Column
    private int p3Range;

    public int getModelId() {
        return modelId;
    }

    public void setModelId(int modelId) {
        this.modelId = modelId;
    }

    public int getP1Payload() {
        return p1Payload;
    }

    public void setP1Payload(int p1Payload) {
        this.p1Payload = p1Payload;
    }

    public int getP1Range() {
        return p1Range;
    }

    public void setP1Range(int p1Range) {
        this.p1Range = p1Range;
    }

    public int getP2Payload() {
        return p2Payload;
    }

    public void setP2Payload(int p2Payload) {
        this.p2Payload = p2Payload;
    }

    public int getP2Range() {
        return p2Range;
    }

    public void setP2Range(int p2Range) {
        this.p2Range = p2Range;
    }

    public int getP3Range() {
        return p3Range;
    }

    public void setP3Range(int p3Range) {
        this.p3Range = p3Range;
    }
}
