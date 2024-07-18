import java.util.concurrent.atomic.AtomicReference;
import java.util.Optional;

public interface FormattedText {
    FormattedText EMPTY = new FormattedText() {
        @Override
        public <T> Optional<T> visit(AtomicReference<T> p_130797_) {
            return Optional.empty();
        }
    };

    <T> Optional<T> visit(AtomicReference<T> p_ref);

    static FormattedText of(final String p_txt) {
        class Local {
            void run(int p_127434_) {}
        }
        return new FormattedText() {
            @Override
            public <T> Optional<T> visit(AtomicReference<T> p_130787_) {
                return Optional.empty();
            }
        };
    }
}
