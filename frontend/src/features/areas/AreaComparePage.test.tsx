import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { AreaComparePage } from "./AreaComparePage";
import type { PhotoDto } from "../../shared/api/types";

const useAreaPhotosQuery = vi.fn();
const useInspectionsQuery = vi.fn();

vi.mock("./hooks", () => ({
  useAreaPhotosQuery: (...args: unknown[]) => useAreaPhotosQuery(...args),
  useInspectionsQuery: (...args: unknown[]) => useInspectionsQuery(...args),
}));

function photo(overrides: Partial<PhotoDto> = {}): PhotoDto {
  return {
    id: "photo-1",
    url: "https://storage.local/photo.jpg",
    caption: null,
    featured: false,
    archived: false,
    takenAt: "2026-09-01",
    createdAt: "2026-09-01T10:00:00Z",
    uploadedBy: { id: "u1", name: "Síndico" },
    inspectionId: null,
    ...overrides,
  };
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/areas/area-1/comparar"]}>
      <Routes>
        <Route path="/areas/:id/comparar" element={<AreaComparePage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("AreaComparePage (RF-ARE-08)", () => {
  it("mostra estado vazio quando não há duas datas com fotos", () => {
    useAreaPhotosQuery.mockReturnValue({ data: [photo()], isLoading: false });
    useInspectionsQuery.mockReturnValue({ data: [], isLoading: false });
    renderPage();
    expect(
      screen.getByText(/Ainda não há duas datas com fotos suficientes/),
    ).toBeInTheDocument();
  });

  it("mostra as duas colunas lado a lado ao escolher duas datas (RF-ARE-08)", async () => {
    useAreaPhotosQuery.mockReturnValue({
      data: [
        photo({ id: "p1", takenAt: "2026-09-01" }),
        photo({ id: "p2", takenAt: "2026-06-01" }),
      ],
      isLoading: false,
    });
    useInspectionsQuery.mockReturnValue({ data: [], isLoading: false });
    const user = userEvent.setup();
    renderPage();

    await user.selectOptions(screen.getByLabelText("Data (esquerda)"), "2026-09-01");
    await user.selectOptions(screen.getByLabelText("Data (direita)"), "2026-06-01");

    expect(screen.getByRole("heading", { name: "01/09/2026" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "01/06/2026" })).toBeInTheDocument();
    expect(screen.getAllByRole("img")).toHaveLength(2);
  });
});
