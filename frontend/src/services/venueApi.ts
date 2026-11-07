import { apiDelete, apiGet, apiPost, apiPut, apiUpload } from './apiClient';
import type { Booking } from './bookingApi';

export type Money = number | string;

export interface Movie {
  id: number;
  title: string;
  languages: string;
  certificate: 'U' | 'UA' | 'A';
  genres: string;
  durationMinutes: number;
  synopsis?: string;
  cast?: string;
  releaseDate: string;
  posterFrom: string;
  posterTo: string;
  /** Poster photo; without one the app draws a poster from the two colours. */
  posterUrl?: string;
}

export interface NowShowing {
  movie: Movie;
  theatres: number;
  nextShow: string;
  /** What it plays in nearby this week. */
  formats: string[];
  languages: string[];
}

export interface Show {
  id: number;
  startsAt: string;
  format: string;
  language: string;
  screenName: string;
  seatsLeft: number;
  totalSeats: number;
  fromPrice: Money;
}

export interface TheatreShows {
  organizationId: number;
  theatreName: string;
  slug: string;
  branchId: number;
  branchName: string;
  locality?: string;
  city: string;
  distanceKm: number;
  shows: Show[];
}

export interface MoviePage {
  movie: Movie;
  date: string;
  dates: string[];
  theatres: TheatreShows[];
  formats: string[];
  languages: string[];
}

export interface SeatRow {
  row: string;
  tier: string;
  seats: number;
  gapsAfter: number[];
}

export interface SeatMap {
  showtimeId: number;
  startsAt: string;
  format: string;
  language: string;
  movie: Movie;
  theatreName: string;
  branchName: string;
  screenName: string;
  rows: SeatRow[];
  prices: Record<string, Money>;
  taken: string[];
  bookable: boolean;
}

export interface TheatreShow {
  id: number;
  startsAt: string;
  format: string;
  language: string;
  screen: string;
  movieId: number;
  title: string;
  certificate: string;
}

export interface Restaurant {
  organizationId: number;
  slug: string;
  name: string;
  kind: string;
  ratingAvg: Money;
  reviewCount: number;
  coverUrl?: string;
  cuisines?: string;
  costForTwo?: Money;
  vegOnly: boolean;
  offer?: string;
  branchId: number;
  locality?: string;
  city: string;
  distanceKm: number;
}

export interface MenuItem {
  id: number;
  name: string;
  description?: string;
  price: Money;
  veg: boolean;
  bestseller: boolean;
}

export interface DiningDetails {
  cuisines?: string;
  costForTwo?: Money;
  vegOnly: boolean;
  offer?: string;
  features: string[];
  maxPartySize: number;
  menu: { name: string; items: MenuItem[] }[];
}

export interface TableSlot {
  time: string;
  startsAt: string;
  available: boolean;
}

// ----- owner / admin -----

export interface VenueProfile {
  cuisines?: string;
  costForTwo?: Money;
  vegOnly: boolean;
  capacityPerSlot?: number;
  offerText?: string;
  features?: string;
}

export interface MenuLine extends MenuItem {
  section: string;
}

export interface Screen {
  id: number;
  branchId: number;
  branchName: string;
  name: string;
  rows: SeatRow[];
  totalSeats: number;
  tiers: string[];
}

export interface ShowLine {
  id: number;
  screenId: number;
  screenName: string;
  movieId: number;
  movieTitle: string;
  startsAt: string;
  endsAt: string;
  format: string;
  language: string;
  prices: Record<string, Money>;
  sold: number;
  totalSeats: number;
}

export interface VenueSetup {
  kind?: 'CINEMA' | 'DINING';
  profile?: VenueProfile;
  menu: MenuLine[];
  screens: Screen[];
  shows: ShowLine[];
}

export interface CatalogMovie extends Movie {
  active: boolean;
}

export type MovieInput = Omit<Movie, 'id' | 'posterUrl'>;

type Area = { lat: number; lng: number; radiusKm?: number };

export const venueApi = {
  movies: (a: Area) => apiGet<NowShowing[]>('/public/movies', { params: a }),
  upcoming: () => apiGet<Movie[]>('/public/movies/upcoming'),
  movie: (id: number, a: Area & { date?: string }) => apiGet<MoviePage>(`/public/movies/${id}`, { params: a }),
  seats: (showtimeId: number) => apiGet<SeatMap>(`/public/showtimes/${showtimeId}`),
  theatreSchedule: (orgId: number) => apiGet<TheatreShow[]>(`/public/theatres/${orgId}/schedule`),
  buyTickets: (showtimeId: number, seats: string[]) => apiPost<Booking>('/customer/tickets', { showtimeId, seats }),

  restaurants: (a: Area & { q?: string }) => apiGet<Restaurant[]>('/public/dining', { params: a }),
  dining: (slug: string) => apiGet<DiningDetails>(`/public/dining/${encodeURIComponent(slug)}`),
  tableSlots: (branchId: number, date: string, partySize: number) =>
    apiGet<TableSlot[]>(`/public/dining/branches/${branchId}/slots`, { params: { date, partySize } }),
  bookTable: (body: { branchId: number; startsAt: string; partySize: number; note?: string }) =>
    apiPost<Booking>('/customer/table-bookings', body),

  setup: (orgId: number) => apiGet<VenueSetup>(`/owner/organizations/${orgId}/venue`),
  saveProfile: (orgId: number, body: Partial<VenueProfile>) => apiPut<VenueSetup>(`/owner/organizations/${orgId}/venue`, body),
  addDish: (orgId: number, body: { section: string; name: string; description?: string; price: Money; veg: boolean; bestseller: boolean }) =>
    apiPost<VenueSetup>(`/owner/organizations/${orgId}/venue/menu`, body),
  deleteDish: (orgId: number, id: number) => apiDelete<VenueSetup>(`/owner/organizations/${orgId}/venue/menu/${id}`),
  addScreen: (orgId: number, body: { branchId: number; name: string; rows: SeatRow[] }) =>
    apiPost<VenueSetup>(`/owner/organizations/${orgId}/venue/screens`, body),
  deleteScreen: (orgId: number, id: number) => apiDelete<VenueSetup>(`/owner/organizations/${orgId}/venue/screens/${id}`),
  addShow: (orgId: number, body: { screenId: number; movieId: number; startsAt: string; format: string; language: string; prices: Record<string, Money> }) =>
    apiPost<VenueSetup>(`/owner/organizations/${orgId}/venue/showtimes`, body),
  deleteShow: (orgId: number, id: number) => apiDelete<VenueSetup>(`/owner/organizations/${orgId}/venue/showtimes/${id}`),
  ownerMovies: () => apiGet<CatalogMovie[]>('/owner/movies'),

  adminMovies: () => apiGet<CatalogMovie[]>('/admin/movies'),
  addMovie: (body: MovieInput) => apiPost<CatalogMovie>('/admin/movies', body),
  setPoster: (id: number, file: File) => apiUpload<CatalogMovie>(`/admin/movies/${id}/poster`, file),
  setMovieActive: (id: number, active: boolean) => apiPost<CatalogMovie>(`/admin/movies/${id}/active`, { active }),
};

/** Seats in a row, numbered from 1, with an aisle after each gap. */
export function seatLabel(row: string, n: number) {
  return `${row}${n}`;
}
