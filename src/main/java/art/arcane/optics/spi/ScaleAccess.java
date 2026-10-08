package art.arcane.optics.spi;

public interface ScaleAccess<E> {
    double scale(E entity);

    double defaultScale(E entity);

    boolean scale(E entity, double factor);

    boolean reset(E entity);
}
