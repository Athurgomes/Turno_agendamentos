import { describe, expect, it, vi } from "vitest";

const apiFetch = vi.fn();
const apiFetchBlob = vi.fn();

vi.mock("../../shared/api/client", () => ({ apiFetch, apiFetchBlob }));

const { getSummary, getReservationsByMonth, exportFile } = await import("./api");

describe("dashboard api (RF-DAS-01/02/03, docs/03 'Dashboard e exportação')", () => {
  it("getSummary chama /dashboard/summary com from e to", async () => {
    apiFetch.mockResolvedValue({});
    await getSummary({ from: "2026-09-01", to: "2026-09-30" });
    expect(apiFetch).toHaveBeenCalledWith("/dashboard/summary?from=2026-09-01&to=2026-09-30");
  });

  it("getReservationsByMonth chama /dashboard/reservations-by-month só com to", async () => {
    apiFetch.mockResolvedValue([]);
    await getReservationsByMonth("2026-09-30");
    expect(apiFetch).toHaveBeenCalledWith("/dashboard/reservations-by-month?to=2026-09-30");
  });

  it("exportFile chama /exports/{type}?from&to&format e monta o nome padrão do arquivo", async () => {
    apiFetchBlob.mockResolvedValue({ blob: new Blob(), filename: "turno-reservations-2026-09-01-a-2026-09-30.csv" });
    await exportFile("reservations", "csv", { from: "2026-09-01", to: "2026-09-30" });
    expect(apiFetchBlob).toHaveBeenCalledWith(
      "/exports/reservations?from=2026-09-01&to=2026-09-30&format=csv",
      "turno-reservations-2026-09-01-a-2026-09-30.csv",
    );
  });
});
