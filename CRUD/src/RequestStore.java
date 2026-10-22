/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.swing.table.DefaultTableModel;
/**
 *
 * @author Ej B. Gonzales
 */
public final class RequestStore {

    private static final Path FILE = Paths.get("requests.txt").toAbsolutePath();

    private RequestStore() {
    }

    public static final class Request {
        public final String name;
        public final String studentId;
        public final String document;
        public final String purpose;
        public final String status;

        
        private final int index;
        private final List<String> sourceLines;

        private Request(
                String name,
                String studentId,
                String document,
                String purpose,
                String status,
                int index,
                List<String> sourceLines) {

            this.name = name;
            this.studentId = studentId;
            this.document = document;
            this.purpose = purpose;
            this.status = status;
            this.index = index;
            this.sourceLines = sourceLines;
        }

        public Object[] toRow() {
            return new Object[]{
                name, studentId, document, purpose, status
            };
        }

        private String toLine() {
            return String.join(" | ",
                    name, studentId, document, purpose, status);
        }
    }

    public static synchronized List<Request> load()
            throws IOException {

        return parse(readLines());
    }

    public static synchronized void add(
            String studentId,
            String name,
            String document,
            String purpose) throws IOException {

        validate(studentId, name, document, purpose);

        List<Request> requests = load();

        requests.add(new Request(
                name.trim(),
                studentId.trim(),
                document.trim(),
                purpose.trim(),
                "Pending",
                requests.size(),
                Collections.emptyList()));

        save(requests);
    }

    public static synchronized void updateStudentRequest(
            Request selected,
            String studentId,
            String document,
            String purpose) throws IOException {

        validate(studentId, document, purpose);

        List<Request> requests = freshSelection(selected);
        Request old = requests.get(selected.index);

        if (!old.studentId.equals(studentId)) {
            throw new IOException(
                    "You cannot update another student's request.");
        }

        if (!old.status.equals("Pending")) {
            throw new IOException(
                    "Only Pending requests can be updated.");
        }

        requests.set(selected.index, new Request(
                old.name,
                old.studentId,
                document.trim(),
                purpose.trim(),
                old.status,
                old.index,
                old.sourceLines));

        save(requests);
    }

    public static synchronized void changeStatus(
            Request selected,
            String status) throws IOException {

        if (!"Approved".equals(status)
                && !"Rejected".equals(status)) {

            throw new IOException("Invalid request status.");
        }

        List<Request> requests = freshSelection(selected);
        Request old = requests.get(selected.index);

        requests.set(selected.index, new Request(
                old.name,
                old.studentId,
                old.document,
                old.purpose,
                status,
                old.index,
                old.sourceLines));

        save(requests);
    }

    public static synchronized void delete(Request selected)
            throws IOException {

        List<Request> requests = freshSelection(selected);
        requests.remove(selected.index);
        save(requests);
    }

    public static DefaultTableModel newTableModel() {
        return new DefaultTableModel(
                new Object[]{
                    "Student Name",
                    "Student ID",
                    "Document Type",
                    "Purpose",
                    "Status"
                }, 0) {

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static List<String> readLines() throws IOException {
        if (!Files.exists(FILE)) {
            return Collections.emptyList();
        }

        return Files.readAllLines(
                FILE, StandardCharsets.UTF_8);
    }

    private static List<Request> parse(List<String> lines)
            throws IOException {

        List<String> snapshot = Collections.unmodifiableList(
                new ArrayList<>(lines));

        List<Request> requests = new ArrayList<>();

        for (int lineNumber = 0;
                lineNumber < lines.size();
                lineNumber++) {

            String line = lines.get(lineNumber);

            if (line.trim().isEmpty()) {
                continue;
            }

            String[] fields = line.split("\\|", -1);

            for (int i = 0; i < fields.length; i++) {
                fields[i] = fields[i].trim();
            }

            int offset = 0;

            if (fields.length == 6) {
               
                try {
                    UUID.fromString(fields[0]);
                } catch (IllegalArgumentException e) {
                    throw new IOException(
                            "Invalid request ID at line "
                            + (lineNumber + 1), e);
                }

                offset = 1;

            } else if (fields.length != 5) {
                throw new IOException(
                        "Invalid request format at line "
                        + (lineNumber + 1)
                        + ". File was not changed.");
            }

            try {
                validate(
                        fields[offset],
                        fields[offset + 1],
                        fields[offset + 2],
                        fields[offset + 3],
                        fields[offset + 4]);

                String status = fields[offset + 4];

                if (!status.equals("Pending")
                        && !status.equals("Approved")
                        && !status.equals("Rejected")) {

                    throw new IOException(
                            "Unknown request status.");
                }

            } catch (IOException e) {
                throw new IOException(
                        "Invalid request at line "
                        + (lineNumber + 1)
                        + ": " + e.getMessage(), e);
            }

            requests.add(new Request(
                    fields[offset],
                    fields[offset + 1],
                    fields[offset + 2],
                    fields[offset + 3],
                    fields[offset + 4],
                    requests.size(),
                    snapshot));
        }

        return requests;
    }

    private static List<Request> freshSelection(Request selected)
            throws IOException {

        if (selected == null) {
            throw new IOException("Select a request first.");
        }

        List<String> currentLines = readLines();

        if (!currentLines.equals(selected.sourceLines)) {
            throw new IOException(
                    "Requests have changed. "
                    + "Refresh and select the request again.");
        }

        List<Request> requests = parse(currentLines);

        if (selected.index < 0
                || selected.index >= requests.size()) {

            throw new IOException(
                    "Request no longer exists. "
                    + "Refresh and try again.");
        }

        return requests;
    }

    private static void validate(String... values)
            throws IOException {

        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                throw new IOException(
                        "All request fields are required.");
            }

            if (value.contains("|")
                    || value.contains("\n")
                    || value.contains("\r")) {

                throw new IOException(
                        "Request fields cannot contain "
                        + "| or line breaks.");
            }
        }
    }

    private static void save(List<Request> requests)
            throws IOException {

        List<String> lines = new ArrayList<>();

        for (Request request : requests) {
            lines.add(request.toLine());
        }

        Path temp = Files.createTempFile(
                FILE.getParent(), "requests-", ".tmp");

        try {
            Files.write(
                    temp, lines, StandardCharsets.UTF_8);

            try {
                Files.move(
                        temp,
                        FILE,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);

            } catch (AtomicMoveNotSupportedException e) {
                Files.move(
                        temp,
                        FILE,
                        StandardCopyOption.REPLACE_EXISTING);
            }

        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
