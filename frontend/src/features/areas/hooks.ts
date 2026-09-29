/** Hooks TanStack Query sobre `./api.ts` — telas de áreas comuns (`/areas/**`). */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as api from "./api";
import type { ListAreaPhotosParams, ListAreasParams, UploadAreaPhotosParams } from "./api";
import type { CreateAreaPayload, UpdateAreaPayload } from "./areaFormSchema";
import type { CreateInspectionData } from "./api";

export function useAreaCategoriesQuery() {
  return useQuery({
    queryKey: ["area-categories"] as const,
    queryFn: () => api.listAreaCategories(),
    staleTime: Infinity,
  });
}

export function useAreasQuery(params: ListAreasParams) {
  return useQuery({
    queryKey: ["areas", params] as const,
    queryFn: () => api.listAreas(params),
  });
}

export function useAreaQuery(id: string | undefined) {
  return useQuery({
    queryKey: ["areas", "detail", id] as const,
    queryFn: () => api.getArea(id as string),
    enabled: !!id,
  });
}

export function useCreateArea() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ payload, photos }: { payload: CreateAreaPayload; photos: File[] }) =>
      api.createArea(payload, photos),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["areas"] });
    },
  });
}

export function useUpdateArea(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateAreaPayload) => api.updateArea(id, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["areas"] });
    },
  });
}

export function useUpdateAreaStatus(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: api.AreaStatusPayload) => api.updateAreaStatus(id, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["areas"] });
    },
  });
}

export function useDeleteArea(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: api.DeleteAreaPayload) => api.deleteArea(id, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["areas"] });
    },
  });
}

export function useAreaPhotosQuery(areaId: string | undefined, params: ListAreaPhotosParams) {
  return useQuery({
    queryKey: ["areas", areaId, "photos", params] as const,
    queryFn: () => api.getAreaPhotos(areaId as string, params),
    enabled: !!areaId,
  });
}

export function useUploadAreaPhotos(areaId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ photos, params }: { photos: File[]; params?: UploadAreaPhotosParams }) =>
      api.uploadAreaPhotos(areaId, photos, params),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["areas", areaId] });
    },
  });
}

export function useUpdateAreaPhoto(areaId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      photoId,
      payload,
    }: {
      photoId: string;
      payload: { caption?: string; featured?: boolean };
    }) => api.updateAreaPhoto(areaId, photoId, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["areas", areaId] });
    },
  });
}

export function useArchiveAreaPhoto(areaId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (photoId: string) => api.archiveAreaPhoto(areaId, photoId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["areas", areaId] });
    },
  });
}

export function useInspectionsQuery(areaId: string | undefined) {
  return useQuery({
    queryKey: ["areas", areaId, "inspections"] as const,
    queryFn: () => api.listInspections(areaId as string),
    enabled: !!areaId,
  });
}

export function useCreateInspection(areaId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ data, photos }: { data: CreateInspectionData; photos: File[] }) =>
      api.createInspection(areaId, data, photos),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["areas", areaId] });
    },
  });
}
