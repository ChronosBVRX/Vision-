export type ZeichnenUndErratenWordCategory = "standard" | "adult" | "all";

export interface ZeichnenUndErratenLobbyCategoryOption {
  id: ZeichnenUndErratenWordCategory;
  label: string;
  description: string;
}

export interface ZeichnenUndErratenLobbyState {
  selectedCategory: ZeichnenUndErratenWordCategory;
  categories: ZeichnenUndErratenLobbyCategoryOption[];
}
