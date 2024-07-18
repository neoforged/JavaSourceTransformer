import java.util.concurrent.atomic.AtomicReference;
import java.util.Optional;

public interface FormattedText {
    FormattedText EMPTY = new FormattedText() {
        @Override
        public <T> Optional<T> visit(AtomicReference<T> named_inner1) {
            return Optional.empty();
        }
    };

    <T> Optional<T> visit(AtomicReference<T> p_ref);

    static FormattedText of(final String p_txt) {
        class Local {
            void run(int named_local1) {}
        }
        return new FormattedText() {
            @Override
            public <T> Optional<T> visit(AtomicReference<T> named_inner2) {
                return Optional.empty();
            }
        };
    }
}
