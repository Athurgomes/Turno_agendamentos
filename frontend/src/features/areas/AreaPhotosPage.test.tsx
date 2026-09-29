import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { AreaPhotosPage } from "./AreaPhotosPage";
import type { PhotoDto } from "../../shared/api/types";

const useAreaPhotosQuery = vi.fn();
const archiveMutateAsync = vi.fn();
const updateMutateAsync = vi.fn();
const uploadMutateAsync = vi.fn();

vi.mock("./hooks", () => ({
  useAreaPhotosQuery: (...args: unknown[]) => useAreaPhotosQuery(...args),
  useArchiveAreaPhoto: () => ({ mutateAsync: archiveMutateAsync, isPending: false }),
  useUpdateAreaPhoto: () => ({ mutateAsync: updateMutateAsync, isPending: false }),
  useUploadAreaPhotos: () => ({ mutateAsync: uploadMutateAsync, isPending: false }),
}));

const sessionMock = vi.hoisted(() => ({
  user: { id: "1", role: "SYNDIC", name: "Síndico", unitId: null, unitIdentifier: null, tempPassword: false },
}));
vi.mock("../auth/useSession", () => ({ useSession: () => sessionMock }));

function photo(overrides: Partial<PhotoDto> = {}): PhotoDto {
  return {
    id: "photo-1",
    url: "https://storage.local/photo.jpg",
    caption: "Depois da reforma",
    featured: true,
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
    <MemoryRouter initialEntries={["/areas/area-1/fotos"]}>
      <Routes>
        <Route path="/areas/:id/fotos" element={<AreaPhotosPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("AreaPhotosPage", () => {
  it("RN-17: SYNDIC não vê o botão Arquivar (só ADMIN arquiva)", () => {
    sessionMock.user.role = "SYNDIC";
    useAreaPhotosQuery.mockReturnValue({ data: [photo()], isLoading: false });
    renderPage();
    expect(screen.queryByRole("button", { name: "Arquivar" })).not.toBeInTheDocument();
  });

  it("RN-17: ADMIN vê Arquivar e precisa confirmar antes de chamar a API", async () => {
    sessionMock.user.role = "ADMIN";
    useAreaPhotosQuery.mockReturnValue({ data: [photo()], isLoading: false });
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "Arquivar" }));
    expect(archiveMutateAsync).not.toHaveBeenCalled();

    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Arquivar" }));
    expect(archiveMutateAsync).toHaveBeenCalledWith("photo-1");
  });

  it("D-52: conta UNIT recebe uploadedBy null e a página renderiza sem o autor", () => {
    sessionMock.user.role = "SYNDIC";
    useAreaPhotosQuery.mockReturnValue({ data: [photo({ uploadedBy: null })], isLoading: false });
    renderPage();
    expect(screen.getByText("01/09/2026")).toBeInTheDocument();
    expect(screen.queryByText(/—/)).not.toBeInTheDocument();
  });
});
