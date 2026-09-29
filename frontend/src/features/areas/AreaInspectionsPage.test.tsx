import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { AreaInspectionsPage } from "./AreaInspectionsPage";
import type { InspectionDto } from "../../shared/api/types";

const useInspectionsQuery = vi.fn();
const createMutateAsync = vi.fn();

vi.mock("./hooks", () => ({
  useInspectionsQuery: (...args: unknown[]) => useInspectionsQuery(...args),
  useCreateInspection: () => ({ mutateAsync: createMutateAsync, isPending: false }),
}));

function inspection(overrides: Partial<InspectionDto> = {}): InspectionDto {
  return {
    id: "insp-1",
    inspectedAt: "2026-08-01",
    overallCondition: "GOOD",
    notes: "Tudo certo.",
    author: { id: "u1", name: "Síndico" },
    photos: [],
    ...overrides,
  };
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/areas/area-1/vistorias"]}>
      <Routes>
        <Route path="/areas/:id/vistorias" element={<AreaInspectionsPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("AreaInspectionsPage (RF-ARE-07)", () => {
  it("lista as vistorias com data, estado e autor (RF-ARE-07)", () => {
    useInspectionsQuery.mockReturnValue({ data: [inspection()], isLoading: false });
    renderPage();
    const item = screen.getByText("01/08/2026").closest("li") as HTMLElement;
    expect(within(item).getByText("Bom")).toBeInTheDocument();
    expect(within(item).getByText("Vistoriado por Síndico")).toBeInTheDocument();
  });

  it("exige data antes de registrar uma nova vistoria (RF-ARE-07)", async () => {
    useInspectionsQuery.mockReturnValue({ data: [], isLoading: false });
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "Registrar vistoria" }));

    expect(await screen.findByText("Informe a data da vistoria.")).toBeInTheDocument();
    expect(createMutateAsync).not.toHaveBeenCalled();
  });
});
