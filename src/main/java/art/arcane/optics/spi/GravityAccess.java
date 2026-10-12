package art.arcane.optics.spi;

import art.arcane.optics.crossing.GravityFrame;

public interface GravityAccess<E> {
    boolean supported(E entity);

    GravityFrame gravity(E entity);

    boolean gravity(E entity, GravityFrame frame);

    boolean reset(E entity);
}
