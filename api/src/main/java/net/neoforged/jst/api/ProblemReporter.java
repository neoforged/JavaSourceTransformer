package net.neoforged.jst.api;

/**
 * Report problems from plugins.
 */
public interface ProblemReporter {
    ProblemReporter NOOP = new ProblemReporter() {
        @Override
        public void report(ProblemId problemId, ProblemSeverity severity, ProblemLocation location, String message) {
        }

        @Override
        public void report(ProblemId problemId, ProblemSeverity severity, String message) {
        }
    };

    void report(ProblemId problemId, ProblemSeverity severity, ProblemLocation location, String message);

    /**
     * Reports a location independent problem.
     */
    void report(ProblemId problemId, ProblemSeverity severity, String message);

}
